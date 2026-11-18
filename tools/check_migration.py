"""Checks a Room migration against the exported schemas: builds the old database, runs the migration's SQL,
then compares every table with a database built straight from the new schema. Run after a build:

    python tools/check_migration.py 1 2
"""
import json
import os
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
SCHEMAS = os.path.join(ROOT, "app", "schemas", "com.bleepp.data.BleeppDb")
SOURCE = os.path.join(ROOT, "app", "src", "main", "java", "com", "bleepp", "data", "Database.kt")


def build(version):
    schema = json.load(open(os.path.join(SCHEMAS, f"{version}.json"), encoding="utf-8"))["database"]
    db = sqlite3.connect(":memory:")
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db, [e["tableName"] for e in schema["entities"]]


def migration_sql(old, new):
    source = open(SOURCE, encoding="utf-8").read()
    body = re.search(rf"MIGRATION_{old}_{new} = object : Migration\({old}, {new}\) \{{(.*?)\n\}}", source, re.S).group(1)
    consts = dict(re.findall(r'const val (\w+) = "([^"]*)"', source))
    statements = []
    for call in re.findall(r"db\.execSQL\((.*?)\)\n", body, re.S):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', call)
        sql = "".join(parts)
        sql = re.sub(r"\$(\w+)", lambda m: consts.get(m.group(1), m.group(0)), sql)
        statements.append(sql)
    return statements


def columns(db, table):
    return sorted((r[1], r[2].upper(), bool(r[3]), r[4], r[5]) for r in db.execute(f"PRAGMA table_info(`{table}`)"))


old, new = int(sys.argv[1]), int(sys.argv[2])
migrated, _ = build(old)
migrated.execute("INSERT INTO log (sbnKey, pkg, appName, title, text, postedAt, outcome) VALUES ('k', 'p', 'App', 'Hi', 'there', 1, 1)")
migrated.execute("INSERT INTO rules VALUES ('p', 1, 'bleep', 70, '', 0, 0, 30, 10, 1)")
for sql in migration_sql(old, new):
    migrated.execute(sql)
fresh, tables = build(new)
problems = 0
for table in tables:
    a, b = columns(migrated, table), columns(fresh, table)
    if a != b:
        problems += 1
        print(f"MISMATCH in {table}:\n  migrated {a}\n  expected {b}")
print("kept log row:", migrated.execute("SELECT title, text, channelId, bookmarked FROM log").fetchall())
print("kept rule:", migrated.execute("SELECT pkg, sound, ringForCalls, callSound FROM rules").fetchall())
print("OK" if not problems else f"{problems} table(s) differ")
sys.exit(1 if problems else 0)

package app

import (
	"context"
	"database/sql"
	_ "embed"
	"github.com/jmoiron/sqlx"
	"log"
	"os"
)

const TestDb = "./othellocord-temp.db"

//go:embed schema.sql
var CreateSchema string

type CtxQuerier interface {
	GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
}

func createTestDB() (*sqlx.DB, func()) {
	fail := func(err error) {
		log.Fatalf("failed to open test sqlite db: %v", err)
	}

	db, err := sqlx.Open("sqlite", TestDb)
	if err != nil {
		fail(err)
	}
	closer := func() {
		_ = db.Close()
		if err := os.Remove(TestDb); err != nil {
			fail(err)
		}
	}
	if _, err := db.Exec(CreateSchema); err != nil {
		fail(err)
	}
	return db, closer
}

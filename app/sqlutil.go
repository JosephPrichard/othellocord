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

type Querier interface {
	GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
	SelectContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error
}

func createTestDB() (*sqlx.DB, func()) {
	db, err := sqlx.Open("sqlite", TestDb)
	if err != nil {
		log.Fatalf("failed to open test sqlite db: %v", err)
	}
	closer := func() {
		_ = db.Close()
		if err := os.Remove(TestDb); err != nil {
			log.Fatalf("failed to remove test sqlite file: %v", err)
		}
	}
	if _, err := db.Exec(CreateSchema); err != nil {
		log.Fatalf("failed to create test schema: %v", err)
	}
	return db, closer
}

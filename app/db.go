package app

import (
	"context"
	"database/sql"
	"errors"
	"log"
	"os"
)

const TestDb = "./othellocord-temp.schema"

var CreateTable = `
	CREATE TABLE IF NOT EXISTS stats (
	    player_id TEXT PRIMARY KEY, 
	    elo FLOAT NOT NULL, 
	    won INTEGER NOT NULL, 
	    drawn INTEGER NOT NULL, 
	    lost INTEGER NOT NULL
	);
	CREATE TABLE IF NOT EXISTS games (
	    id TEXT NOT NULL,
	    board TEXT NOT NULL,
	    white_id TEXT NOT NULL,
	    black_id TEXT NOT NULL,
	    white_name TEXT NOT NULL,
		black_name TEXT NOT NULL,
		moves TEXT NOT NULL,
		expire_time INTEGER NOT NULL,
		PRIMARY KEY (id)
	);

	CREATE INDEX IF NOT EXISTS idx_stats_elo ON stats(elo);
	CREATE INDEX IF NOT EXISTS idx_games_expire_time ON games(expire_time);
	CREATE INDEX IF NOT EXISTS idx_games_player_ids ON games(white_id, black_id);
	`

var ErrExpectedOneRow = errors.New("expected at least one row")

type Query interface {
	Query(query string, args ...any) (*sql.Rows, error)
	QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error)
	Exec(query string, args ...any) (sql.Result, error)
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
}

func createTestDB() (*sql.DB, func()) {
	db, err := sql.Open("sqlite", TestDb)
	if err != nil {
		log.Fatalf("failed to open test sqlite db: %v", err)
	}
	closer := func() {
		_ = db.Close()
		if err := os.Remove(TestDb); err != nil {
			log.Fatalf("failed to remove test sqlite db: %v", err)
		}
	}
	if _, err := db.Exec(CreateTable); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}
	return db, closer
}

package app

import (
	"context"
	"database/sql"
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
	CREATE TABLE IF NOT EXISTS bot_tasks (
	 	game_id TEXT NOT NULL,
	 	channel_id TEXT NOT NULL,
	    push_time INTEGER NOT NULL
    );

	CREATE INDEX idx_stats_elo ON stats(elo);
	CREATE INDEX idx_games_expire_time ON games(expire_time);
	CREATE INDEX idx_game_id ON bot_tasks(game_id);
	CREATE INDEX idx_push_time ON bot_tasks(push_time);
	`

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

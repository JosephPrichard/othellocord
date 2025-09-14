package app

import (
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
	    board TEXT NOT NULL,
	    white_id TEXT NOT NULL,
	    black_id TEXT NOT NULL,
	    white_name TEXT NOT NULL,
		black_name TEXT NOT NULL,
		moves TEXT NOT NULL,
		expire_time INTEGER NOT NULL,
		PRIMARY KEY (white_id, black_id)
	);
	`

type Query interface {
	Query(query string, args ...any) (*sql.Rows, error)
	Exec(query string, args ...any) (sql.Result, error)
}

func createTestDB() (*sql.DB, func()) {
	db, err := sql.Open("sqlite", TestDb)
	if err != nil {
		log.Fatal(err)
	}
	closer := func() {
		if err := db.Close(); err != nil {
			log.Fatal(err)
		}
		if err := os.Remove(TestDb); err != nil {
			log.Fatal(err)
		}
	}
	if _, err := db.Exec(CreateTable); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}
	return db, closer
}

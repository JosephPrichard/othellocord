package main

import (
	"database/sql"
	"log"
	_ "modernc.org/sqlite"
	"othellocord/app"
)

func main() {
	db, err := sql.Open("sqlite", "./othellocord.db")
	if err != nil {
		log.Fatal(err)
	}
	defer func() {
		_ = db.Close()
	}()
	if _, err := db.Exec(app.CreateTable); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}
}

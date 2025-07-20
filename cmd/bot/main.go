package main

import (
	"database/sql"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/joho/godotenv"
	"log"
	"log/slog"
	_ "modernc.org/sqlite"
	"os"
	"os/signal"
	"othellocord/app/bot"
	"othellocord/app/othello"
	"syscall"
)

var CreateSchema = `
	CREATE TABLE IF NOT EXISTS stats (
	    player_id TEXT PRIMARY KEY,
		elo    FLOAT,
		won    INTEGER,
		drawn  INTEGER,
		lost   INTEGER
	);`

func main() {
	// read environment variables into memory
	if err := godotenv.Load(); err != nil {
		panic("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")

	// create the database client
	db, err := sql.Open("sqlite", "./othellocord.db")
	if err != nil {
		log.Fatal(err)
	}
	defer func() {
		if err := db.Close(); err != nil {
			slog.Error("failed to close database", "error", err)
		}
	}()
	if _, err := db.Exec(CreateSchema); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}

	// construct the discord client
	dg, err := discordgo.New(fmt.Sprintf("Bot %s", token))
	if err != nil {
		log.Fatalf("failed to construct discord client: %v", err)
	}
	defer func() {
		if err := dg.Close(); err != nil {
			slog.Error("failed to close discord dg", "error", err)
		}
	}()

	agentChan := bot.NewAgentQueue()

	// create the commands object and subscribe
	h := bot.Handler{
		Db: db,
		Cc: bot.NewChallengeCache(),
		Gs: bot.NewGameStore(db),
		Uc: bot.NewUserCache(dg),
		Rc: othello.NewRenderCache(),
		Aq: agentChan,
	}

	dg.AddHandler(h.HandleCommand)

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, syscall.SIGINT, syscall.SIGTERM, os.Interrupt)

	slog.Info("starting othellocord bot")
	if err = dg.Open(); err != nil {
		log.Fatalf("failed to connect to discord: %v", err)
	}

	slog.Info("othellocord bot is listening for events")
	<-signalChan
}

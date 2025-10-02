package main

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/jmoiron/sqlx"
	"github.com/joho/godotenv"
	"log"
	"log/slog"
	_ "modernc.org/sqlite"
	"os"
	"os/signal"
	"othellocord/app"
	"syscall"
)

func main() {
	if err := godotenv.Load(); err != nil {
		slog.Info("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")
	path := os.Getenv("NTEST_PATH")

	db, err := sqlx.Connect("sqlite", "./othellocord.db?_busy_timeout=5000")
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()

	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if _, err := db.Exec(app.CreateSchema); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}

	dg, _ := discordgo.New(fmt.Sprintf("Bot %s", token))
	defer func() {
		_ = dg.Close()
	}()

	sh, err := app.StartNTestShell(path)
	if err != nil {
		log.Fatalf("failed to open ntest shell: %v", err)
	}

	go sh.ListenRequests()
	go app.ExpireGamesCron(db)

	state := app.MakeState(db, dg, sh)
	dg.AddHandler(state.HandeInteractionCreate)

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, syscall.SIGINT, syscall.SIGTERM, os.Interrupt)

	slog.Info("starting othellocord service")
	if err = dg.Open(); err != nil {
		log.Fatalf("failed to connect to events: %v", err)
	}

	slog.Info("othellocord service is listening for events")
	<-signalChan
}

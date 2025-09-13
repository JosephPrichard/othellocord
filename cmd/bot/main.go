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
	"othellocord/app"
	"syscall"
)

func main() {
	if err := godotenv.Load(); err != nil {
		panic("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")

	db, err := sql.Open("sqlite", "./othellocord.schema")
	if err != nil {
		log.Fatal(err)
	}
	defer func() {
		_ = db.Close()
	}()
	if _, err := db.Exec(app.CreateTable); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}

	dg, _ := discordgo.New(fmt.Sprintf("Bot %s", token))
	defer func() {
		_ = dg.Close()
	}()

	go app.ExpireGamesCron(db)

	h := app.Handler{
		Db:              db,
		Renderer:        app.MakeRenderCache(),
		ChallengeCache:  app.MakeChallengeCache(),
		UserCache:       app.MakeUserCache(dg),
		SimulationCache: app.MakeSimCache(),
	}

	dg.AddHandler(h.HandeInteractionCreate)

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, syscall.SIGINT, syscall.SIGTERM, os.Interrupt)

	slog.Info("starting othellocord service")
	if err = dg.Open(); err != nil {
		log.Fatalf("failed to connect to events: %v", err)
	}

	slog.Info("othellocord service is listening for events")
	<-signalChan
}

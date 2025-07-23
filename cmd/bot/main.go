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

func main() {
	if err := godotenv.Load(); err != nil {
		panic("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")

	db, err := sql.Open("sqlite", "./othellocord.db")
	if err != nil {
		log.Fatal(err)
	}
	defer func() {
		_ = db.Close()
	}()
	if _, err := db.Exec(bot.CreateSchema); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}

	dg, _ := discordgo.New(fmt.Sprintf("Bot %s", token))
	defer func() {
		_ = dg.Close()
	}()

	h := bot.Handler{
		Db: db,
		Cc: bot.NewChallengeCache(),
		Gs: bot.WithEviction(db),
		Uc: bot.NewUserCache(dg),
		Rc: othello.NewRenderCache(),
		Aq: bot.NewAgentQueue(),
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

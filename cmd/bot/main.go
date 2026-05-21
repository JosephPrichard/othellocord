package main

import (
	"fmt"
	"log"
	"log/slog"
	"os"
	"os/signal"
	"othellocord/app"
	"strconv"
	"syscall"

	"github.com/bwmarrin/discordgo"
	"github.com/jmoiron/sqlx"
	"github.com/joho/godotenv"
	_ "modernc.org/sqlite"
)

func main() {
	slog.Info("starting othellocord service")

	if err := godotenv.Load(); err != nil {
		slog.Info("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")
	path := os.Getenv("NTEST_PATH")
	shellCountStr := os.Getenv("SHELL_COUNT")

	if shellCountStr == "" {
		shellCountStr = "1"
	}
	shellCount, err := strconv.Atoi(shellCountStr)
	if err != nil {
		log.Fatalf("node count envvar must be an integer: %v", err)
	}

	db, err := sqlx.Connect("sqlite", "./othellocord.db?_busy_timeout=5000")
	if err != nil {
		log.Fatalf("failed to open db file: %v", err)
	}
	defer db.Close()

	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if _, err := db.Exec(app.CreateSchema); err != nil {
		log.Fatalf("failed to create schema: %v", err)
	}

	discord, _ := discordgo.New(fmt.Sprintf("Bot %s", token))
	defer discord.Close()

	shell, err := app.MakeShellPool(path, shellCount)
	if err != nil {
		log.Fatalf("failed to open ntest shell: %v", err)
	}

	go app.ExpireGamesCron(db)

	state := app.MakeState(db, discord, shell)
	discord.AddHandler(app.MakeHandleInteractionCreate(&state))

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, syscall.SIGINT, syscall.SIGTERM, os.Interrupt)

	if err = discord.Open(); err != nil {
		log.Fatalf("failed to connect to events: %v", err)
	}

	slog.Info("othellocord service is listening for events")
	<-signalChan
}

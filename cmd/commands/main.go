package main

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/joho/godotenv"
	"log"
	"log/slog"
	"os"
	"othellocord/app"
)

func main() {
	// read environment variables into memory
	if err := godotenv.Load(); err != nil {
		log.Print("failed to load .env file")
	}

	token := os.Getenv("DISCORD_TOKEN")
	appID := os.Getenv("DISCORD_APP_ID")

	dg, err := discordgo.New(fmt.Sprintf("Bot %s", token))
	if err != nil {
		log.Fatalf("failed to construct discord client: %v", err)
	}
	defer func() {
		if err := dg.Close(); err != nil {
			slog.Error("failed to close discord dg", "err", err)
		}
	}()

	if _, err := dg.ApplicationCommandBulkOverwrite(appID, "", app.Commands); err != nil {
		log.Fatalf("failed to bulk overwrite commands: %v", err)
	}
}

package discord

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"log/slog"
	"strconv"
	"strings"
)

type Commands struct {
	db *sql.DB
	uc UserCache
}

func (c *Commands) HandleCommand(d *discordgo.Session, i *discordgo.InteractionCreate) {
	switch i.ApplicationCommandData().Name {
	case "challenge":
		c.HandleChallenge(d, i)
	case "accept":
		c.HandleAccept(d, i)
	case "forfeit":
		c.HandleForfeit(d, i)
	case "move":
		c.HandleMove(d, i)
	case "view":
		c.HandleView(d, i)
	case "analyze":
		c.HandleAnalyze(d, i)
	case "simulate":
		c.HandleSimulate(d, i)
	case "stats":
		c.HandleStats(d, i)
	case "leaderboard":
		c.HandleLeaderboard(d, i)
	}
}

func (c *Commands) HandleChallenge(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleAccept(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleForfeit(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleMove(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleView(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleAnalyze(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) HandleSimulate(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

var UserNotProvided = errors.New("user not provided")

func (c *Commands) HandleStats(d *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.Background()

	var user *discordgo.User
	var stats Stats
	var err error

	userOpt := i.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		user, err = FetchUser(ctx, c.uc, userOpt.Value.(string))
		if err != nil {
			handleError(d, i.ChannelID, err)
			return
		}
	} else {
		user = i.User
	}
	if user == nil {
		handleError(d, i.ChannelID, UserNotProvided)
		return
	}

	if stats, err = ReadStats(ctx, c.db, c.uc, user.ID); err != nil {
		handleError(d, i.ChannelID, err)
		return
	}

	embed := discordgo.MessageEmbed{
		Title: fmt.Sprintf("%s's stats", user.Username),
		Fields: []*discordgo.MessageEmbedField{
			{Name: "Rating", Value: fmt.Sprintf("%0.2f", stats.Elo), Inline: false},
			{Name: "Win Rate", Value: stats.WinRate(), Inline: false},
			{Name: "Won", Value: strconv.Itoa(stats.Won), Inline: true},
			{Name: "Lost", Value: strconv.Itoa(stats.Lost), Inline: true},
			{Name: "Drawn", Value: strconv.Itoa(stats.Drawn), Inline: true},
		},
		Thumbnail: &discordgo.MessageEmbedThumbnail{
			URL:    user.AvatarURL("1024"),
			Width:  1024,
			Height: 1024,
		},
	}

	if _, err := d.ChannelMessageSendEmbed(i.ChannelID, &embed); err != nil {
		slog.Error("failed to send embed in HandleStats", err)
	}
}

func (c *Commands) HandleLeaderboard(d *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.Background()

	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, c.db, c.uc); err != nil {
		handleError(d, i.ChannelID, err)
		return
	}

	var desc strings.Builder
	desc.WriteString("```\n")
	for i, stats := range stats {
		desc.WriteString(rightPad(fmt.Sprintf("%d)", i), 4))
		desc.WriteString(leftPad(stats.Player.Name, 32))
		desc.WriteString(leftPad(fmt.Sprintf("%.2f", stats.Elo), 12))
		desc.WriteString("\n")
	}
	desc.WriteString("```")

	embed := discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       0x00ff00,
	}

	if _, err := d.ChannelMessageSendEmbed(i.ChannelID, &embed); err != nil {
		slog.Error("failed to send embed in HandleLeaderboard", err)
	}
}

var knownErrors = map[string]struct{}{
	UserNotProvided.Error(): {},
}

func handleError(d *discordgo.Session, channelID string, err error) {
	slog.Error("error when handling command", "error", err)

	resp := "an unexpected error occurred"

	msg := err.Error()
	if _, ok := knownErrors[msg]; ok {
		resp = msg
	}

	_, err = d.ChannelMessageSend(channelID, resp)
	if err != nil {
		slog.Error("failed to send embed in handleError", err)
	}
}

package discord

import (
	"database/sql"
	"github.com/bwmarrin/discordgo"
)

type Commands struct {
	db *sql.DB
	c  UserCache
}

func (c *Commands) handleCommand(d *discordgo.Session, i *discordgo.InteractionCreate) {
	switch i.ApplicationCommandData().Name {
	case "challenge":
		c.handleChallenge(d, i)
	case "accept":
		c.handleAccept(d, i)
	case "forfeit":
		c.handleForfeit(d, i)
	case "move":
		c.handleMove(d, i)
	case "view":
		c.handleView(d, i)
	case "analyze":
		c.handleAnalyze(d, i)
	case "simulate":
		c.handleSimulate(d, i)
	case "stats":
		c.handleStats(d, i)
	case "leaderboard":
		c.handleLeaderboard(d, i)
	}
}

func (c *Commands) handleChallenge(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleAccept(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleForfeit(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleMove(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleView(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleAnalyze(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleSimulate(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleStats(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

func (c *Commands) handleLeaderboard(d *discordgo.Session, i *discordgo.InteractionCreate) {

}

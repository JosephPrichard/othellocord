package app

import (
	"fmt"
	"github.com/bwmarrin/discordgo"
)

const MinDelay = 1
const MaxDelay = 5

var LevelDesc = fmt.Sprintf("Level of the service between %d and %d", MinBotLevel, MaxBotLevel)
var ExpectedTileValue = "be a string of the form 'a1' where 'a' is the column and '1' is the row"
var DelayDesc = fmt.Sprintf("Minimum delay between Moves in seconds between %d and %d secs", MinDelay, MaxDelay)

var Commands = []*discordgo.ApplicationCommand{
	{
		Name:        "challenge",
		Description: "Challenges the service or another user to an Othello Game",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:        discordgo.ApplicationCommandOptionSubCommand,
				Name:        "user",
				Description: "Challenges another user to a Game",
				Options: []*discordgo.ApplicationCommandOption{
					{
						Type:        discordgo.ApplicationCommandOptionUser,
						Name:        "opponent",
						Description: "The opponent to challenge",
						Required:    true,
					},
				},
			},
			{
				Type:        discordgo.ApplicationCommandOptionSubCommand,
				Name:        "service",
				Description: "Challenges the service to a Game",
				Options: []*discordgo.ApplicationCommandOption{
					{
						Type:        discordgo.ApplicationCommandOptionInteger,
						Name:        "level",
						Description: LevelDesc,
						Required:    false,
					},
				},
			},
		},
	},
	{
		Name:        "accept",
		Description: "Accepts a challenge from another discord user",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:        discordgo.ApplicationCommandOptionUser,
				Name:        "challenger",
				Description: "User who made the challenge",
				Required:    true,
			},
		},
	},
	{
		Name:        "forfeit",
		Description: "Forfeits the user's current Game",
	},
	{
		Name:        "move",
		Description: "Makes a move on user's current Game",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:         discordgo.ApplicationCommandOptionString,
				Name:         "move",
				Description:  "Move to make on the OthelloBoard",
				Required:     true,
				Autocomplete: true,
			},
		},
	},
	{
		Name:        "view",
		Description: "Displays the Game State including all the Moves that can be made this turn",
	},
	{
		Name:        "analyze",
		Description: "Runs an analysis of the OthelloBoard",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:        discordgo.ApplicationCommandOptionInteger,
				Name:        "level",
				Description: LevelDesc,
				Required:    false,
			},
		},
	},
	{
		Name:        "simulate",
		Description: "Simulates a Game between two bots",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:        discordgo.ApplicationCommandOptionInteger,
				Name:        "black-level",
				Description: LevelDesc,
				Required:    false,
			},
			{
				Type:        discordgo.ApplicationCommandOptionInteger,
				Name:        "white-level",
				Description: LevelDesc,
				Required:    false,
			},
			{
				Type:        discordgo.ApplicationCommandOptionInteger,
				Name:        "delay",
				Description: DelayDesc,
				Required:    false,
			},
		},
	},
	{
		Name:        "stats",
		Description: "Retrieves the stats profile for a player",
		Options: []*discordgo.ApplicationCommandOption{
			{
				Type:        discordgo.ApplicationCommandOptionUser,
				Name:        "player",
				Description: "Player to get stats profile for",
				Required:    false,
			},
		},
	},
	{
		Name:        "leaderboard",
		Description: "Retrieves the highest rated players by ELO",
	},
}

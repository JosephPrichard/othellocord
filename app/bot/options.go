package bot

import (
	"context"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"othellocord/app/othello"
	"strings"
	"time"
)

func getSubcommand(i *discordgo.InteractionCreate) (string, []*discordgo.ApplicationCommandInteractionDataOption) {
	cmd := i.ApplicationCommandData()
	if len(cmd.Options) > 0 {
		firstOpt := cmd.Options[0]
		if firstOpt.Type == discordgo.ApplicationCommandOptionSubCommand {
			return firstOpt.Name, firstOpt.Options
		}
	}
	return "", nil
}

func (h Handler) getPlayerOpt(ctx context.Context, options []*discordgo.ApplicationCommandInteractionDataOption, name string) (Player, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		opponent, err := h.Uc.FetchPlayer(ctx, opt.Value.(string))
		if err != nil {
			return Player{}, fmt.Errorf("failed to get player option name=%s, err: %w", name, err)
		}
		return opponent, nil
	}
	return Player{}, OptError{Name: name}
}

const DefaultLevel = 5

func getLevelOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (int, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		value, ok := opt.Value.(float64)
		if !ok {
			return 0, OptError{Name: name, InvalidValue: opt.Value}
		}
		level := int(value)
		if !IsValidBotLevel(level) {
			return 0, OptError{Name: name, InvalidValue: level}
		}
		return level, nil
	}
	return DefaultLevel, nil
}

const DefaultDelay = time.Second * 2

func getDelayOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (time.Duration, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		value, ok := opt.Value.(float64)
		if !ok {
			return 0, OptError{Name: name, InvalidValue: opt.Value}
		}
		delay := int(value)
		if delay < MinDelay || delay > MaxDelay {
			return 0, OptError{Name: name, InvalidValue: delay}
		}
		return time.Second * time.Duration(delay), nil
	}
	return DefaultDelay, nil
}

func getTileOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (othello.Tile, string, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		value, ok := opt.Value.(string)
		if !ok {
			return othello.Tile{}, "", OptError{Name: name, InvalidValue: opt.Value, ExpectedValue: ExpectedTileValue}
		}
		tile, err := othello.TileFromNotation(value)
		if err != nil {
			return othello.Tile{}, "", OptError{Name: name, InvalidValue: opt.Value, ExpectedValue: ExpectedTileValue}
		}
		return tile, value, nil
	}
	return othello.Tile{}, "", OptError{Name: name, ExpectedValue: ExpectedTileValue}
}

func formatOptions(options []*discordgo.ApplicationCommandInteractionDataOption) string {
	var sb strings.Builder
	sb.WriteRune('[')
	for i, opt := range options {
		sb.WriteString(opt.Name)
		if i != len(options)-1 {
			sb.WriteString(", ")
		}
	}
	sb.WriteRune(']')
	return sb.String()
}

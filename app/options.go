package app

import (
	"context"
	"fmt"
	"github.com/bwmarrin/discordgo"
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

func (h *Handler) getPlayerOpt(ctx context.Context, options []*discordgo.ApplicationCommandInteractionDataOption, name string) (Player, error) {
	for _, opt := range options {
		if opt.Name != name {
			continue
		}
		opponent, err := h.UserCache.GetPlayer(ctx, opt.Value.(string))
		if err != nil {
			return Player{}, fmt.Errorf("failed to get player option name=%s, err: %s", name, err)
		}
		return opponent, nil
	}
	return Player{}, OptionError{Name: name}
}

const DefaultLevel = 3

func getLevelOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (uint64, error) {
	var option *discordgo.ApplicationCommandInteractionDataOption
	for _, opt := range options {
		if opt.Name == name {
			option = opt
			break
		}
	}
	if option == nil {
		return DefaultLevel, nil
	}

	value, ok := option.Value.(float64)
	if !ok {
		return 0, OptionError{Name: name, InvalidValue: option.Value}
	}
	level := uint64(value)
	if IsInvalidBotLevel(level) {
		return 0, OptionError{Name: name, InvalidValue: level}
	}
	return level, nil
}

const DefaultDelay = time.Second * 2

func getDelayOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (time.Duration, error) {
	var option *discordgo.ApplicationCommandInteractionDataOption
	for _, opt := range options {
		if opt.Name == name {
			option = opt
			break
		}
	}
	if option == nil {
		return DefaultDelay, nil
	}

	value, ok := option.Value.(float64)
	if !ok {
		return 0, OptionError{Name: name, InvalidValue: option.Value}
	}
	delay := int(value)
	if delay < MinDelay || delay > MaxDelay {
		return 0, OptionError{Name: name, InvalidValue: delay}
	}
	return time.Second * time.Duration(delay), nil
}

func getTileOpt(options []*discordgo.ApplicationCommandInteractionDataOption, name string) (Tile, string, error) {
	makeErrRet := func(err error) (Tile, string, error) {
		return Tile{}, "", err
	}

	var option *discordgo.ApplicationCommandInteractionDataOption

	for _, opt := range options {
		if opt.Name == name {
			option = opt
			break
		}

	}
	if option == nil {
		return makeErrRet(OptionError{Name: name, ExpectedValue: ExpectedTileValue})
	}

	value, ok := option.Value.(string)
	if !ok {
		return makeErrRet(OptionError{Name: name, InvalidValue: value, ExpectedValue: ExpectedTileValue})
	}
	tile, err := ParseTileSafe(value)
	if err != nil {
		return makeErrRet(OptionError{Name: name, InvalidValue: value, ExpectedValue: ExpectedTileValue})
	}
	return tile, value, nil
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

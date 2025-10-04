package app

import (
	"context"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"slices"
	"strings"
	"time"
)

type Option = discordgo.ApplicationCommandInteractionDataOption

func getSubcommand(i *discordgo.InteractionCreate) (string, []*Option) {
	cmd := i.ApplicationCommandData()
	if len(cmd.Options) > 0 {
		firstOpt := cmd.Options[0]
		if firstOpt.Type == discordgo.ApplicationCommandOptionSubCommand {
			return firstOpt.Name, firstOpt.Options
		}
	}
	return "", nil
}

func getOpt(options []*Option, name string) *Option {
	index := slices.IndexFunc(options, func(opt *Option) bool {
		return opt.Name == name
	})
	if index > 0 {
		return options[index]
	}
	return nil
}

func getPlayerOpt(ctx context.Context, uc *UserCache, options []*Option, name string) (Player, error) {
	option := getOpt(options, name)
	if option == nil {
		return Player{}, OptionError{Name: name}
	}

	name, ok := option.Value.(string)
	if !ok {
		return Player{}, fmt.Errorf("expected player to be string, was: %T", option.Value)
	}
	opponent, err := uc.GetPlayer(ctx, name)
	if err != nil {
		return Player{}, fmt.Errorf("failed to get player option name=%s, err: %s", name, err)
	}
	return opponent, nil
}

func getDefaultPlayer(ctx context.Context, uc *UserCache, ic *discordgo.InteractionCreate) (*discordgo.User, error) {
	var user *discordgo.User
	var err error

	userOpt := ic.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		un, ok := userOpt.Value.(string)
		if !ok {
			return nil, fmt.Errorf("expected player to be string, was: %T", userOpt.Value)
		}
		if user, err = uc.GetUser(ctx, un); err != nil {
			return nil, err
		}
	} else if ic.Interaction.Member != nil {
		user = ic.Interaction.Member.User
	}

	return user, nil
}

const DefaultLevel = 3

func getLevelOpt(options []*Option, name string) (uint64, error) {
	option := getOpt(options, name)
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

func getDelayOpt(options []*Option, name string) (time.Duration, error) {
	option := getOpt(options, name)
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

func getTileOpt(options []*Option, name string) (Tile, string, error) {
	fail := func(err error) (Tile, string, error) {
		return Tile{}, "", err
	}

	option := getOpt(options, name)
	if option == nil {
		return fail(OptionError{Name: name, ExpectedValue: ExpectedTileValue})
	}

	value, ok := option.Value.(string)
	if !ok {
		return fail(OptionError{Name: name, InvalidValue: value, ExpectedValue: ExpectedTileValue})
	}
	tile, err := ParseTileSafe(value)
	if err != nil {
		return fail(OptionError{Name: name, InvalidValue: value, ExpectedValue: ExpectedTileValue})
	}
	return tile, value, nil
}

func formatOptions(options []*Option) string {
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

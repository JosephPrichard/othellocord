package discord

import (
	"context"
	"errors"
	"fmt"
	"github.com/allegro/bigcache/v2"
	"github.com/bwmarrin/discordgo"
	cache2 "github.com/eko/gocache/lib/v4/cache"
	"log/slog"
	"strconv"
)

type Player struct {
	Id   string
	Name string
}

const MaxBotLevel = 6

func GetBotName(playerId string) string {
	if id, err := strconv.Atoi(playerId); err != nil && id <= MaxBotLevel {
		return fmt.Sprintf("OthelloBot level %d", id)
	}
	return ""
}

type UserCache struct {
	cache *cache2.Cache[*discordgo.User]
	d     *discordgo.Session
}

func FetchUsername(ctx context.Context, c UserCache, playerId string) (string, error) {
	user, err := FetchUser(ctx, c, playerId)
	if err != nil || user == nil {
		return "", err
	}
	return user.Username, nil
}

func FetchUser(ctx context.Context, c UserCache, playerId string) (*discordgo.User, error) {
	trace := ctx.Value("trace")

	user, err := c.cache.Get(ctx, playerId)

	if errors.Is(err, bigcache.ErrEntryNotFound) || user == nil {
		user, err := c.d.User(playerId)
		if err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "error", err)
			return nil, err
		}
		if err := c.cache.Set(ctx, playerId, user); err != nil {
			slog.Error("failed to set user in cache", "trace", trace, "error", err)
		}
		return user, nil
	} else if err != nil {
		slog.Error("failed to get user from cache", "trace", trace, "error", err)
		return user, err
	}

	return user, nil
}

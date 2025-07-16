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
	Id   uint64
	Name string
}

const MaxBotLevel = 6

func GetBotName(playerId uint64) string {
	if playerId <= MaxBotLevel {
		return fmt.Sprintf("OthelloBot level %d", playerId)
	}
	return ""
}

type UserCache struct {
	cache *cache2.Cache[string]
	d     *discordgo.Session
}

func FetchUsername(ctx context.Context, c UserCache, playerId uint64) (string, error) {
	trace := ctx.Value("trace")

	idStr := strconv.FormatUint(playerId, 10)
	username, err := c.cache.Get(ctx, idStr)

	if errors.Is(err, bigcache.ErrEntryNotFound) {
		user, err := c.d.User(idStr)
		if err != nil {
			slog.Error("failed to fetch username from discord", "trace", trace, "error", err)
			return "", err
		}
		if err := c.cache.Set(ctx, idStr, user.Username); err != nil {
			slog.Error("failed to set username in cache", "trace", trace, "error", err)
		}
		return user.Username, nil
	} else if err != nil {
		slog.Error("failed to get username from cache", "trace", trace, "error", err)
		return username, err
	}

	return username, nil
}

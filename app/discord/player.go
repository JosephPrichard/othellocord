package discord

import (
	"context"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/coocood/freecache"
	"github.com/eko/gocache/lib/v4/cache"
	"github.com/eko/gocache/lib/v4/store"
	freecachestore "github.com/eko/gocache/store/freecache/v4"
	"log/slog"
	"strconv"
	"time"
)

const MaxBotLevel = 6

type Player struct {
	Id   string
	Name string
}

func (player Player) Tag() string {
	if id, err := strconv.Atoi(player.Id); err != nil && !IsValidBotLevel(id) {
		return fmt.Sprintf("<@%s> ", player.Id)
	}
	return ""
}

func PlayerFromUser(user *discordgo.User) Player {
	if user == nil {
		panic("expected user to be non nil when creating player")
	}
	return Player{Id: user.ID, Name: user.Username}
}

func GetBotName(playerId string) string {
	if id, err := strconv.Atoi(playerId); err != nil && IsValidBotLevel(id) {
		return GetBotLevel(id)
	}
	return ""
}

func IsValidBotLevel(level int) bool {
	return level <= MaxBotLevel
}

func GetBotLevel(level int) string {
	return fmt.Sprintf("OthelloBot level %d", level)
}

type UserCache struct {
	Store *cache.Cache[*discordgo.User]
	Dg    *discordgo.Session
}

func NewUserCache(dg *discordgo.Session) UserCache {
	freeCache := freecachestore.NewFreecache(freecache.NewCache(512), store.WithExpiration(30*time.Minute))
	s := cache.New[*discordgo.User](freeCache)
	return UserCache{
		Store: s,
		Dg:    dg,
	}
}

func FetchUsername(ctx context.Context, uc UserCache, playerId string) (string, error) {
	user, err := FetchUser(ctx, uc, playerId)
	if err != nil || user == nil {
		return "", err
	}
	return user.Username, nil
}

func FetchPlayer(ctx context.Context, uc UserCache, playerId string) (Player, error) {
	user, err := FetchUser(ctx, uc, playerId)
	if err != nil || user == nil {
		return Player{}, err
	}
	return PlayerFromUser(user), nil
}

func FetchUser(ctx context.Context, uc UserCache, playerId string) (*discordgo.User, error) {
	trace := ctx.Value("trace")

	user, err := uc.Store.Get(ctx, playerId)

	if errors.Is(err, freecache.ErrNotFound) || user == nil {
		user, err := uc.Dg.User(playerId)
		if err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "error", err)
			return nil, err
		}
		if err := uc.Store.Set(ctx, playerId, user); err != nil {
			slog.Error("failed to set user in cache", "trace", trace, "error", err)
		}
		return user, nil
	} else if err != nil {
		slog.Error("failed to get user from cache", "trace", trace, "error", err)
		return user, err
	}

	return user, nil
}

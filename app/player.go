package app

import (
	"context"
	"fmt"
	"log/slog"
	"strconv"
	"time"

	"github.com/bwmarrin/discordgo"
	"github.com/jellydator/ttlcache/v3"
)

const MinBotLevel = 1
const MaxBotLevel = 5

type Player struct {
	ID    string
	Name  string
	Level uint64 // only used for bot levels
}

func MakeHumanPlayer(user *discordgo.User) Player {
	return Player{ID: user.ID, Name: user.Username}
}

func MakeBotPlayer(level uint64) Player {
	return Player{ID: fmt.Sprintf("%d", level), Name: fmt.Sprintf("NTest level %d", level), Level: level}
}

func MakePlayer(id string, name string) Player {
	var player Player

	if botId, err := strconv.Atoi(id); err == nil && IsValidBotLevel(uint64(botId)) {
		player = Player{ID: id, Name: fmt.Sprintf("NTest level %d", botId), Level: uint64(botId)}
	} else {
		player = Player{ID: id, Name: name}
	}

	return player
}

func LevelToDepth(level uint64) uint64 {
	switch level {
	case 1:
		return 5
	case 2:
		return 8
	case 3:
		return 12
	case 4:
		return 15
	case 5:
		return 20
	}
	return 0
}

func (player Player) LevelToDepth() uint64 {
	return LevelToDepth(player.Level)
}

func (player Player) IsHuman() bool {
	return player.Level == 0
}

func (player Player) IsBot() bool {
	return player.Level != 0
}

func IsInvalidBotLevel(level uint64) bool {
	return level < MinBotLevel || level > MaxBotLevel
}

func IsValidBotLevel(level uint64) bool {
	return !IsInvalidBotLevel(level)
}

type UserFetcher interface {
	User(userID string, options ...discordgo.RequestOption) (st *discordgo.User, err error)
}

type UserCacheApi interface {
	GetUsername(ctx context.Context, playerID string) (string, error)
	GetPlayer(ctx context.Context, playerID string) (Player, error)
}

type UserCache struct {
	Cache *ttlcache.Cache[string, discordgo.User]
	Uf    UserFetcher
}

func MakeUserCache(uf UserFetcher) UserCache {
	return UserCache{Cache: ttlcache.New[string, discordgo.User](), Uf: uf}
}

func (uc UserCache) GetUsername(ctx context.Context, playerID string) (string, error) {
	user, err := uc.GetUser(ctx, playerID)
	if err != nil {
		return "", err
	}
	return user.Username, nil
}

func (uc UserCache) GetPlayer(ctx context.Context, playerID string) (Player, error) {
	user, err := uc.GetUser(ctx, playerID)
	if err != nil {
		return Player{}, err
	}
	return MakeHumanPlayer(&user), nil
}

const UserCacheTTl = time.Hour

func (uc UserCache) GetUser(ctx context.Context, playerID string) (discordgo.User, error) {
	trace := ctx.Value(TraceKey)

	var user discordgo.User

	item := uc.Cache.Get(playerID)
	if item != nil {
		user = item.Value()
	} else {
		u, err := uc.Uf.User(playerID, discordgo.WithContext(ctx))
		if err != nil {
			slog.Error("failed to fetch user from discord", "trace", trace, "player", playerID, "err", err)
			return discordgo.User{}, err
		}
		user = *u
		uc.Cache.Set(playerID, user, UserCacheTTl)
		slog.Info("set user back into the Cache", "trace", trace, "user", user.Username, "player", playerID)
	}

	slog.Info("fetched user", "trace", trace, "username", user.Username, "ID", playerID)
	return user, nil
}

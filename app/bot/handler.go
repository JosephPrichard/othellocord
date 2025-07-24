package bot

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"github.com/bwmarrin/discordgo"
	"github.com/google/uuid"
	"image"
	"log/slog"
	"othellocord/app/othello"
	"strconv"
	"strings"
	"time"
	"unicode"
)

type Handler struct {
	Db *sql.DB
	Uc UserCache
	Cc ChallengeCache
	Gs *GameStore
	Rc othello.RenderCache
	Eq EngineQ
}

type OptError struct {
	Name          string
	InvalidValue  any
	ExpectedValue string
}

func (e OptError) Error() string {
	expMsg := ""
	if e.ExpectedValue != "" {
		expMsg = fmt.Sprintf(", expected value to be: %s", e.ExpectedValue)
	}
	if e.InvalidValue == "" {
		return fmt.Sprintf("expected an option '%s' to be provided%s", e.Name, expMsg)
	} else {
		return fmt.Sprintf("option '%s' received invalid value '%v'%s", e.Name, e.InvalidValue, expMsg)
	}
}

type SubCmdError struct {
	Name           string
	ExpectedValues []string
}

func (e SubCmdError) Error() string {
	if e.Name == "" {
		return fmt.Sprintf("expected a subcommand with one of following values %v", e.ExpectedValues)
	} else {
		return fmt.Sprintf("invalid subcommand '%s', expected one of following values %v", e.Name, e.ExpectedValues)
	}
}

var ErrUserNotProvided = errors.New("user not provided")

func (h Handler) HandleCommand(dg *discordgo.Session, i *discordgo.InteractionCreate) {
	ctx := context.WithValue(context.Background(), "trace", uuid.NewString())

	cmd := i.ApplicationCommandData()
	slog.Info("received a command", "name", cmd.Name, "options", formatOptions(cmd.Options))

	var err error

	switch cmd.Name {
	case "challenge":
		err = h.HandleChallenge(ctx, dg, i)
	case "accept":
		err = h.HandleAccept(ctx, dg, i)
	case "forfeit":
		err = h.HandleForfeit(ctx, dg, i)
	case "move":
		if i.Interaction.Type == discordgo.InteractionApplicationCommandAutocomplete {
			h.HandleMoveAutocomplete(ctx, dg, i)
		} else {
			err = h.HandleMove(ctx, dg, i)
		}
	case "view":
		err = h.HandleView(ctx, dg, i)
	case "analyze":
		err = h.HandleAnalyze(ctx, dg, i)
	case "simulate":
		err = h.HandleSimulate(ctx, dg, i)
	case "stats":
		err = h.HandleStats(ctx, dg, i)
	case "leaderboard":
		err = h.HandleLeaderboard(ctx, dg, i)
	}
	// if a handler returns an error, it should not have sent an interaction response yet
	if err != nil {
		handleInteractionError(ctx, dg, i, err)
	}
}

var ChallengeSubCmds = []string{"bot", "user"}

func (h Handler) HandleChallenge(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	subCmd, options := getSubcommand(i)
	switch subCmd {
	case "bot":
		return h.HandleBotChallengeCommand(ctx, dg, i, options)
	case "user":
		return h.HandleUserChallengeCommand(ctx, dg, i, options)
	default:
		return SubCmdError{Name: subCmd, ExpectedValues: ChallengeSubCmds}
	}
}

func (h Handler) HandleBotChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	level, err := getLevelOpt(options, "level")
	if err != nil {
		return err
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := CreateBotGame(ctx, h.Gs, player, level)
	if errors.Is(err, ErrAlreadyPlaying) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a game.")); err != nil {
			slog.Error("failed to respond to already playing game for bot challenge", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to create bot game with level=%d, player=%v cmd: %w", level, player, err)
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to bot challenge", "err", err)
	}
	return nil
}

func (h Handler) HandleUserChallengeCommand(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, options []*discordgo.ApplicationCommandInteractionDataOption) error {
	opponent, err := h.getPlayerOpt(ctx, options, "opponent")
	if err != nil {
		return fmt.Errorf("failed to get plater opt: %w", err)
	}

	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	channelID := i.ChannelID
	onExpire := func() {
		if _, err = dg.ChannelMessageSend(channelID, fmt.Sprintf("<@%s> Challenge timed out!", player.ID)); err != nil {
			slog.Error("failed to send user challenge expire", "err", err)
		}
	}
	h.Cc.CreateChallenge(ctx, Challenge{Challenger: player, Challenged: opponent}, onExpire)

	msg := fmt.Sprintf("<@%s>, %s has challenged you to a game of Othello. Type `/accept` <@%s>, or ignore to decline", opponent.ID, player.Name, player.ID)

	if err := dg.InteractionRespond(i.Interaction, createStringResponse(msg)); err != nil {
		slog.Error("failed to respond to user challenge", "err", err)
	}
	return nil
}

func (h Handler) HandleAccept(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	cmd := i.ApplicationCommandData()
	player := PlayerFromUser(i.Interaction.Member.User)

	opponent, err := h.getPlayerOpt(ctx, cmd.Options, "challenger")
	if err != nil {
		return fmt.Errorf("failed to get player opt: %w", err)
	}

	didAccept := h.Cc.AcceptChallenge(ctx, Challenge{Challenged: player, Challenger: opponent})
	if !didAccept {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("Cannot accept a challenge that does not exist.")); err != nil {
			slog.Error("failed to respond to denied accept", "err", err)
		}
		return nil
	}
	game, err := CreateGame(ctx, h.Gs, opponent, player)
	if err != nil {
		return fmt.Errorf("failed to create game with opponent=%v cmd: %w", opponent, err)
	}

	embed := CreateGameStartEmbed(game)
	img := othello.DrawBoard(h.Rc, game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to accept", "err", err)
	}
	return nil
}

func (h Handler) HandleView(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Gs, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		if err := dg.InteractionRespond(i.Interaction, createStringResponse("You're not playing a game.")); err != nil {
			slog.Error("failed to respond to not found view", "err", err)
		}
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get game for player=%s: %w", user.ID, err)
	}

	embed := CreateGameEmbed(game)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to view", "err", err)
	}
	return nil
}

func (h Handler) HandleForfeit(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Gs, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're already in a game."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to get game for player=%s: %w", user.ID, err)
	}
	DeleteGame(h.Gs, game)
	gr := game.CreateForfeitResult(user.ID)
	sr, err := UpdateStats(ctx, h.Db, gr)
	if err != nil {
		return fmt.Errorf("failed to update stats for player=%s: %w", user.ID, err)
	}

	embed := CreateForfeitEmbed(gr, sr)
	img := othello.DrawBoard(h.Rc, game.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to forfeit", "err", err)
	}
	return nil
}

func (h Handler) HandleMoveAutocomplete(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) {
	var moves []othello.Tile
	if i.Interaction.Member != nil {
		game, err := GetGame(ctx, h.Gs, i.Interaction.Member.User.ID)
		if err == nil {
			moves = game.LoadPotentialMoves()
		}
	}

	var choices []*discordgo.ApplicationCommandOptionChoice
	for _, move := range moves {
		tileStr := move.String()
		choices = append(choices, &discordgo.ApplicationCommandOptionChoice{Name: tileStr, Value: tileStr})
	}

	if err := dg.InteractionRespond(i.Interaction, createAutocompleteResponse(choices)); err != nil {
		slog.Error("failed to respond to move autocomplete", "err", err)
	}
}

func (h Handler) handleGameOver(ctx context.Context, game Game, move othello.Tile) (*discordgo.MessageEmbed, image.Image, error) {
	var gr GameResult
	var sr StatsResult
	var err error

	gr = game.CreateResult()
	if sr, err = UpdateStats(ctx, h.Db, gr); err != nil {
		return nil, nil, fmt.Errorf("failed to update stats for result=%v: %w", gr, err)
	}

	embed := CreateGameOverEmbed(game, gr, sr, move)
	img := othello.DrawBoard(h.Rc, game.Board)
	return embed, img, err
}

func (h Handler) handleBotMove(dg *discordgo.Session, channelID string, game Game) {
	request := EngineRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(GetBotLevel(game.CurrentPlayer().ID)),
		T:        GetMoveRequest,
		RespChan: make(chan []othello.RankTile, 1),
	}
	h.Eq.Push(request)

	moves := <-request.RespChan
	slog.Info("received engine response", "moves", moves)
	if len(moves) != 1 {
		panic("expected exactly one engine response in bot move") // we already checked for game over in the caller, we can expect exactly one move
	}
	move := moves[0].Tile

	game = MakeMoveUnchecked(h.Gs, game.OtherPlayer().ID, move) // game will be stored at the ID of the player that is NOT the bot

	embed := CreateGameMoveEmbed(game, move)
	img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

	if _, err := dg.ChannelMessageSendComplex(channelID, createEmbedSend(embed, img)); err != nil {
		slog.Error("failed to send bot move", "err", err)
	}

	// make another move if it is still the bot's move
	if game.CurrentPlayer().isBot() {
		h.handleBotMove(dg, channelID, game)
	}
}

func (h Handler) HandleMove(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	move, moveStr, err := getTileOpt(i.ApplicationCommandData().Options, "move")
	if err != nil {
		return err
	}
	var player Player
	if i.Interaction.Member != nil {
		player = PlayerFromUser(i.Interaction.Member.User)
	} else {
		return ErrUserNotProvided
	}

	game, err := MakeMoveValidated(h.Gs, player.ID, move)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently playing a game."))
		return nil
	} else if errors.Is(err, ErrInvalidMove) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse(fmt.Sprintf("Can't make a move to %s.", moveStr)))
		return nil
	} else if errors.Is(err, ErrTurn) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("It isn't your turn."))
		return nil
	} else if err != nil {
		return fmt.Errorf("failed to make move=%v for player=%s: %w", move, player.ID, err)
	}

	var embed *discordgo.MessageEmbed
	var img image.Image

	if game.IsGameOver() {
		if embed, img, err = h.handleGameOver(ctx, game, move); err != nil {
			return err
		}
	} else {
		isBot := game.CurrentPlayer().isBot()
		if isBot {
			embed = CreateGameEmbed(game)
			go h.handleBotMove(dg, i.ChannelID, game)
		} else {
			embed = CreateGameMoveEmbed(game, move)
		}
		img = othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to move", "err", err)
	}
	return nil
}

func (h Handler) HandleAnalyze(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	trace := ctx.Value("trace")

	ctx, cancel := context.WithDeadline(ctx, time.Now().Add(time.Minute*2))
	defer cancel()

	level, err := getLevelOpt(i.ApplicationCommandData().Options, "level")
	if err != nil {
		return err
	}
	var user *discordgo.User
	if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	} else {
		return ErrUserNotProvided
	}

	game, err := GetGame(ctx, h.Gs, user.ID)
	if errors.Is(err, ErrGameNotFound) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("You're not currently in a game."))
		return nil
	}

	request := EngineRequest{
		Board:    game.Board,
		Depth:    LevelToDepth(level),
		T:        GetMovesRequest,
		RespChan: make(chan []othello.RankTile, 1),
	}
	if !h.Eq.PushSafe(request) {
		_ = dg.InteractionRespond(i.Interaction, createStringResponse("Server is overloaded, try again later."))
		return nil
	}

	if err := dg.InteractionRespond(i.Interaction, createStringResponse("Analyzing... Wait a second...")); err != nil {
		slog.Error("failed to respond to analyze", "err", err)
	}

	select {
	case resp := <-request.RespChan:
		embed := CreateAnalysisEmbed(game, level)
		img := othello.DrawBoardAnalysis(h.Rc, game.Board, resp)

		if _, err := dg.InteractionResponseEdit(i.Interaction, createEmbedEdit(embed, img)); err != nil {
			slog.Error("failed to edit analyze", "err", err)
		}
	case <-ctx.Done():
		slog.Warn("client timed out while waiting for an analysis response", "trace", trace, "err", ctx.Err())
		if _, err := dg.InteractionResponseEdit(i.Interaction, createStringEdit("Timed out while waiting for a response.")); err != nil {
			slog.Error("failed to edit analyze", "err", err)
		}
	}
	return nil
}

type SimMsg struct {
	embed *discordgo.MessageEmbed
	img   image.Image
}

func (h Handler) Simulation(ctx context.Context, initialGame Game, simChan chan SimMsg) {
	trace := ctx.Value("trace")

	var game = initialGame
	var move othello.Tile

	for i := 0; ; i++ {
		request := EngineRequest{
			Board:    game.Board,
			Depth:    LevelToDepth(GetBotLevel(game.CurrentPlayer().ID)),
			T:        GetMoveRequest,
			RespChan: make(chan []othello.RankTile, 1),
		}
		h.Eq.Push(request)

		moves := <-request.RespChan
		slog.Info("received engine response in simulation", "trace", trace, "moves", moves)

		if len(moves) > 0 {
			move = moves[0].Tile
			slog.Info("completed simulation iteration", "index", i, "trace", trace, "move", move)

			game.MakeMove(move)
			game.TrySkipTurn()

			embed := CreateGameEmbed(game)
			img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

			simChan <- SimMsg{img: img, embed: embed}
		} else {
			slog.Info("finished simulation", "trace", trace, "moves", moves, "move", move)

			embed := CreateSimulationEmbed(game, move)
			img := othello.DrawBoardMoves(h.Rc, game.Board, game.LoadPotentialMoves())

			simChan <- SimMsg{img: img, embed: embed}
			close(simChan)
			return
		}
	}
}

func (h Handler) HandleSimulate(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	trace := ctx.Value("trace")
	cmd := i.ApplicationCommandData()

	var whiteLevel int
	var blackLevel int
	var delay time.Duration
	var err error

	if whiteLevel, err = getLevelOpt(cmd.Options, "white-level"); err != nil {
		return err
	}
	if blackLevel, err = getLevelOpt(cmd.Options, "black-level"); err != nil {
		return err
	}
	if delay, err = getDelayOpt(cmd.Options, "delay"); err != nil {
		return err
	}

	initialGame := Game{
		WhitePlayer: PlayerFromLevel(whiteLevel),
		BlackPlayer: PlayerFromLevel(blackLevel),
		Board:       othello.InitialBoard(),
	}
	embed := CreateSimulationStartEmbed(initialGame)
	img := othello.DrawBoard(h.Rc, initialGame.Board)

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, img)); err != nil {
		slog.Error("failed to respond to simulate", "err", err)
	}

	simChan := make(chan SimMsg, othello.BoardSize*othello.BoardSize) // maximum number of possible simulation states
	go h.Simulation(ctx, initialGame, simChan)

	slog.Info("simulation receiver started", "delay", delay, "trace", trace)

	ticker := time.NewTicker(delay)
	for sim := range simChan {
		<-ticker.C
		if _, err := dg.ChannelMessageSendComplex(i.ChannelID, createEmbedSend(sim.embed, sim.img)); err != nil {
			slog.Error("failed to message simulate", "err", err)
		}
	}

	slog.Info("simulation receiver complete", "trace", trace)
	return nil
}

func (h Handler) HandleStats(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var user *discordgo.User
	var err error

	userOpt := i.ApplicationCommandData().GetOption("player")
	if userOpt != nil {
		if user, err = h.Uc.GetUser(ctx, userOpt.Value.(string)); err != nil {
			return err
		}
	} else if i.Interaction.Member != nil {
		user = i.Interaction.Member.User
	}
	if user == nil {
		return ErrUserNotProvided
	}

	var stats Stats
	if stats, err = ReadStats(ctx, h.Db, h.Uc, user.ID); err != nil {
		return err
	}

	embed := &discordgo.MessageEmbed{
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
		Color: GreenColor,
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond to stats", "err", err)
	}
	return nil
}

func (h Handler) HandleLeaderboard(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate) error {
	var stats []Stats
	var err error

	if stats, err = ReadTopStats(ctx, h.Db, h.Uc); err != nil {
		return err
	}

	var desc strings.Builder
	desc.WriteString("```\n")
	for i, stats := range stats {
		desc.WriteString(rightPad(fmt.Sprintf("%d)", i+1), 4))
		desc.WriteString(leftPad(stats.Player.Name, 32))
		desc.WriteString(leftPad(fmt.Sprintf("%.2f", stats.Elo), 12))
		desc.WriteString("\n")
	}
	desc.WriteString("```")

	embed := &discordgo.MessageEmbed{
		Title:       "Leaderboard",
		Description: desc.String(),
		Color:       GreenColor,
	}

	if err := dg.InteractionRespond(i.Interaction, createEmbedResponse(embed, nil)); err != nil {
		slog.Error("failed to respond to leaderboard", "err", err)
	}
	return nil
}

func createMessage(m string) string {
	var sb strings.Builder
	for i, c := range m {
		if i == 0 {
			c = unicode.ToUpper(c)
		}
		sb.WriteRune(c)
	}
	return sb.String()
}

func handleInteractionError(ctx context.Context, dg *discordgo.Session, i *discordgo.InteractionCreate, err error) {
	trace := ctx.Value("trace")
	slog.Error("error when handling command", "trace", trace, "err", err)

	content := "an unexpected error occurred"

	switch err.(type) {
	case *SubCmdError:
		content = err.Error()
	case *OptError:
		content = err.Error()
	}
	content = createMessage(content)

	resp := &discordgo.InteractionResponse{
		Type: discordgo.InteractionResponseChannelMessageWithSource,
		Data: &discordgo.InteractionResponseData{
			Content: content,
		},
	}
	_ = dg.InteractionRespond(i.Interaction, resp)
}

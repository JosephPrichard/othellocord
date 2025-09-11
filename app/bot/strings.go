package bot

import (
	"log/slog"
	"strings"
)

func column(str string, size int, tail string) string {
	var builder strings.Builder
	runes := []rune(str)
	var i int

	for ; i < len(runes) && i < size; i++ {
		builder.WriteRune(runes[i])
	}
	for ; i < size; i++ {
		builder.WriteByte(' ')
	}

	builder.WriteString(tail)
	return builder.String()
}

func rightPad(str string, size int) string {
	return column(str, size, "")
}

func leftPad(str string, size int) string {
	padding := size - len(str)
	if padding > 0 {
		return strings.Repeat(" ", padding) + str
	}
	return str
}

func parseCustomId(customID string) (string, string) {
	index := strings.Index(customID, "+")
	if index == -1 {
		slog.Warn("received a message component without a '+' delimiter", "name", customID)
		return "", ""
	}
	cond := customID[:index]
	key := customID[index+1:]
	return cond, key
}

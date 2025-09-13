package app

import "fmt"

type OptionError struct {
	Name          string
	InvalidValue  any
	ExpectedValue string
}

func (e OptionError) Error() string {
	expMsg := ""
	if e.ExpectedValue != "" {
		expMsg = fmt.Sprintf(", expected value to be: %s", e.ExpectedValue)
	}
	if e.InvalidValue == "" {
		return fmt.Sprintf("Expected an option '%s' to be provided%s", e.Name, expMsg)
	} else {
		return fmt.Sprintf("Option '%s' received invalid value '%v'%s", e.Name, e.InvalidValue, expMsg)
	}
}

type SubCmdError struct {
	Name           string
	ExpectedValues []string
}

func (e SubCmdError) Error() string {
	if e.Name == "" {
		return fmt.Sprintf("Expected a subcommand with one of following values %v", e.ExpectedValues)
	} else {
		return fmt.Sprintf("Invalid subcommand '%s', expected one of following values %v", e.Name, e.ExpectedValues)
	}
}

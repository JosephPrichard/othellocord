package app

import (
	"fmt"
	"github.com/google/go-cmp/cmp"
	"go.uber.org/mock/gomock"
	"reflect"
	"testing"
)

func assertEqual[T any](t *testing.T, expected, actual T, opts ...cmp.Option) bool {
	t.Helper()
	diff := cmp.Diff(expected, actual, opts...)
	isEqual := diff == ""
	if !isEqual {
		t.Errorf("\n%s", diff)
	}
	return isEqual
}

func mockMatcher[T any](t *testing.T, expected T, opts ...cmp.Option) gomock.Matcher {
	return gomock.Cond(func(got T) bool {
		return assertEqual(t, expected, got, opts...)
	})
}

func assertEqualFmt[T fmt.Stringer](t *testing.T, expected, actual T) bool {
	t.Helper()
	isEqual := reflect.DeepEqual(expected, actual)
	if !isEqual {
		t.Errorf("\nexpected: %s\ngot: %s", expected.String(), actual.String())
	}
	return isEqual
}

func mockMatcherFmt[T fmt.Stringer](t *testing.T, expected T) gomock.Matcher {
	return gomock.Cond(func(got T) bool {
		return assertEqualFmt(t, expected, got)
	})
}

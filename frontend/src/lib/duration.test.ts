import { expect, it } from 'vitest'
import { formatBlockDuration, formatDuration, formatRunningTime } from './duration'

it.each([
  [0, '0 mins'], [1, '1 min'], [30, '30 mins'], [60, '1 hour'],
  [90, '1 hour 30 mins'], [120, '2 hours'], [180, '3 hours'],
  [1440, '1 day'], [1530, '1 day 1 hour 30 mins'], [2881, '2 days 1 min'],
])('formats %s minutes as %s', (minutes, expected) => {
  expect(formatDuration(Number(minutes))).toBe(expected)
})

it.each([
  [0, '0m'], [15, '15m'], [45, '45m'], [60, '1h'], [90, '1h 30m'],
  [125, '2h 5m'], [1500, '25h'], [89.9, '1h 29m'], [-5, '0m'],
])('formats Block Duration %s minutes as %s', (minutes, expected) => {
  expect(formatBlockDuration(Number(minutes))).toBe(expected)
})

it.each([
  [0, '0 secs'], [1, '1 sec'], [59, '59 secs'], [59.9, '59 secs'], [60, '1 min'],
  [61, '1 min'], [3599, '59 mins'], [10807, '3 hours'], [-3, '0 secs'],
])('compact Running Time shows seconds only under a minute: %s seconds as %s', (seconds, expected) => {
  expect(formatRunningTime(Number(seconds), false)).toBe(expected)
})

it.each([
  [0, '0 secs'], [1, '1 sec'], [60, '1 min'], [3599, '59 mins 59 secs'],
  [10807, '3 hours 7 secs'], [90061, '1 day 1 hour 1 min 1 sec'],
])('Focus Mode Running Time always shows seconds: %s seconds as %s', (seconds, expected) => {
  expect(formatRunningTime(Number(seconds), true)).toBe(expected)
})

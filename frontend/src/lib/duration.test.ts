import { expect, it } from 'vitest'
import { formatDuration } from './duration'

it.each([
  [0, '0 mins'], [1, '1 min'], [30, '30 mins'], [60, '1 hour'],
  [90, '1 hour 30 mins'], [120, '2 hours'], [180, '3 hours'],
  [1440, '1 day'], [1530, '1 day 1 hour 30 mins'], [2881, '2 days 1 min'],
])('formats %s minutes as %s', (minutes, expected) => {
  expect(formatDuration(Number(minutes))).toBe(expected)
})

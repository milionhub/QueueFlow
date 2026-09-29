import { activity } from './activity'
import { auth } from './auth'
import { board } from './board'
import { common } from './common'
import { dashboard } from './dashboard'
import { members } from './members'
import { projects } from './projects'
import { shell } from './shell'
import { tickets } from './tickets'

/** English: the default language, and the reference every other one is checked against. */
export const en = { common, auth, shell, dashboard, projects, tickets, board, members, activity }

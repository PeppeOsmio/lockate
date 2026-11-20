import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import Box from '@mui/material/Box'
import CardActionArea from '@mui/material/CardActionArea'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'

export interface Stat {
  label: string
  value: ReactNode
  // Shown under the value, like "2 online"
  detail?: ReactNode
  // Where clicking it goes, such as the list it counts
  to?: string
  warning?: boolean
}

export function Stats({ stats }: { stats: Stat[] }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
        gap: 1.5,
        mb: 3,
      }}
    >
      {stats.map((stat) => (
        <Paper
          key={stat.label}
          variant="outlined"
          sx={{ overflow: 'hidden', borderColor: stat.warning ? 'warning.main' : undefined }}
        >
          {stat.to ? (
            <CardActionArea component={RouterLink} to={stat.to} sx={{ height: '100%' }}>
              <StatContent stat={stat} />
            </CardActionArea>
          ) : (
            <StatContent stat={stat} />
          )}
        </Paper>
      ))}
    </Box>
  )
}

function StatContent({ stat }: { stat: Stat }) {
  return (
    <Box sx={{ px: 2, py: 1.5 }}>
      <Typography variant="caption" color="text.secondary">
        {stat.label}
      </Typography>
      <Typography
        variant="h6"
        component="div"
        sx={{ fontSize: '1.1rem' }}
        color={stat.warning ? 'warning.main' : undefined}
      >
        {stat.value}
      </Typography>
      {stat.detail && (
        <Typography variant="caption" color="text.secondary" component="div">
          {stat.detail}
        </Typography>
      )}
    </Box>
  )
}

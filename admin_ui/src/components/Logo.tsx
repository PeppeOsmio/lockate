import LocationOnIcon from '@mui/icons-material/LocationOn'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'

export function Logo() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box
        sx={{
          width: 28,
          height: 28,
          borderRadius: 1.5,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          display: 'grid',
          placeItems: 'center',
        }}
      >
        <LocationOnIcon sx={{ fontSize: 20 }} />
      </Box>
      <Typography variant="subtitle1" component="span" sx={{ fontWeight: 700 }}>
        Lockate Admin
      </Typography>
    </Box>
  )
}

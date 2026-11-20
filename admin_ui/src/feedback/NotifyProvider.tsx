import { useCallback, useState, type ReactNode } from 'react'
import Alert from '@mui/material/Alert'
import Snackbar from '@mui/material/Snackbar'
import { NotifyContext, type Notify } from './notify'

interface Notice {
  key: number
  message: string
  severity: 'success' | 'error'
}

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [notice, setNotice] = useState<Notice | null>(null)
  const [open, setOpen] = useState(false)

  const notify = useCallback<Notify>((message, severity = 'success') => {
    setNotice({ key: Date.now(), message, severity })
    setOpen(true)
  }, [])

  return (
    <NotifyContext.Provider value={notify}>
      {children}
      <Snackbar
        key={notice?.key}
        open={open}
        autoHideDuration={notice?.severity === 'error' ? null : 4000}
        onClose={(_e, reason) => reason !== 'clickaway' && setOpen(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {notice ? (
          <Alert
            severity={notice.severity}
            variant="filled"
            onClose={() => setOpen(false)}
            sx={{ width: '100%' }}
          >
            {notice.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotifyContext.Provider>
  )
}

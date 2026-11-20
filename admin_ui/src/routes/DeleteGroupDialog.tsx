import { useDeleteGroup } from '../api/queries'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useNotify } from '../feedback/notify'
import { shortId } from '../utils/id'

export function DeleteGroupDialog({
  groupId,
  memberCount,
  onDeleted,
  onClose,
}: {
  groupId: string
  memberCount: number
  onDeleted?: () => void
  onClose: () => void
}) {
  const deleteGroup = useDeleteGroup()
  const notify = useNotify()

  return (
    <ConfirmDialog
      title={`Delete group ${shortId(groupId)}?`}
      confirmLabel="Delete group"
      typeToConfirm={shortId(groupId)}
      onConfirm={async () => {
        await deleteGroup.mutateAsync(groupId)
        notify(`Group ${shortId(groupId)} deleted.`)
        onDeleted?.()
      }}
      onClose={onClose}
    >
      The group, its {memberCount === 1 ? 'member' : `${memberCount} members`}, circles and saved
      positions are deleted for good, and the members' apps are told by push (when the server has a
      push relay). This can't be undone.
    </ConfirmDialog>
  )
}

import { useRef, useState } from 'react'
import useAuth from './useAuth.js'
import { followUser, unfollowUser } from '../services/profile.js'

export function updateConnections(list, username, isFollowing) {
  return isFollowing ? [...new Set([...list, username])].sort((a, b) => a.localeCompare(b))
    : list.filter((name) => name !== username)
}

export default function useFollowActions(following, onChanged) {
  const { user } = useAuth()
  const pending = useRef(new Set())
  const [pendingUsers, setPendingUsers] = useState([])
  const [followError, setFollowError] = useState('')

  const toggleFollow = async (username) => {
    if (username === user.username || pending.current.has(username)) return
    const wasFollowing = following.includes(username)
    pending.current.add(username)
    setPendingUsers([...pending.current])
    setFollowError('')
    try {
      await (wasFollowing ? unfollowUser : followUser)(user.username, username)
      onChanged(username, !wasFollowing)
    } catch {
      setFollowError(`No pudimos ${wasFollowing ? 'dejar de seguir' : 'seguir'} a @${username}. Inténtalo de nuevo.`)
    } finally {
      pending.current.delete(username)
      setPendingUsers([...pending.current])
    }
  }
  return { toggleFollow, pendingUsers, followError }
}

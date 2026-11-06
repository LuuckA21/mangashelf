import { useEffect, useState } from 'react'

const PHONE = '(max-width: 700px)' // same breakpoint as styles.css

/** True on phone-width screens; follows rotation and resizes. */
export function useIsPhone() {
  const [phone, setPhone] = useState(
    () => window.matchMedia?.(PHONE).matches ?? false,
  )
  useEffect(() => {
    const mq = window.matchMedia?.(PHONE)
    if (!mq) return
    const update = () => setPhone(mq.matches)
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])
  return phone
}

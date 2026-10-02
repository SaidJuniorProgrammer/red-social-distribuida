import { useEffect } from 'react'

function useFocusOnError(errors, idPrefix) {
  useEffect(() => {
    const firstInvalidField = Object.keys(errors).find((field) => errors[field])

    if (firstInvalidField) {
      document.getElementById(`${idPrefix}-${firstInvalidField}`)?.focus()
    }
  }, [errors, idPrefix])
}

export default useFocusOnError

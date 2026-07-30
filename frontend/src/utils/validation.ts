export const PASSWORD_RULE_MESSAGE = '密码需为8至64位，并同时包含字母和数字'

export function isValidPassword(value: string) {
  return (
    value.length >= 8 &&
    value.length <= 64 &&
    /[A-Za-z]/.test(value) &&
    /\d/.test(value)
  )
}

export function validatePassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  callback(isValidPassword(value) ? undefined : new Error(PASSWORD_RULE_MESSAGE))
}

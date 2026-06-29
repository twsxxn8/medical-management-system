/**
 * 登录态存储：统一使用 sessionStorage。
 * 说明：sessionStorage 按「浏览器标签页」隔离，同一浏览器可分别打开管理员、医生两个标签页联调；
 * 若使用 localStorage，则同源所有标签页共享一份 token，后登录会覆盖先登录。
 */
const KEY_TOKEN = "token";
const KEY_USER_ID = "userId";
const KEY_ROLE = "role";

/** 清除旧版可能写入的 localStorage，避免与 session 混用造成角色/Token 错乱 */
function clearLegacyLocalStorage() {
  try {
    localStorage.removeItem(KEY_TOKEN);
    localStorage.removeItem(KEY_USER_ID);
    localStorage.removeItem(KEY_ROLE);
  } catch {
    /* ignore */
  }
}

export function getToken(): string | null {
  return sessionStorage.getItem(KEY_TOKEN);
}

export function getRole(): string {
  return sessionStorage.getItem(KEY_ROLE) || "";
}

export function setAuth(token: string, userId: string | number, role: string) {
  clearLegacyLocalStorage();
  sessionStorage.setItem(KEY_TOKEN, token);
  sessionStorage.setItem(KEY_USER_ID, String(userId));
  sessionStorage.setItem(KEY_ROLE, role);
}

export function clearAuth() {
  sessionStorage.removeItem(KEY_TOKEN);
  sessionStorage.removeItem(KEY_USER_ID);
  sessionStorage.removeItem(KEY_ROLE);
  clearLegacyLocalStorage();
}

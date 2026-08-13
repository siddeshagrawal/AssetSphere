const RETURN_TO_KEY = "assetsphere:return-to";

function safePath(value: string | null): string | null {
  return value?.startsWith("/") && !value.startsWith("//") ? value : null;
}

export function rememberReturnTo(path: string): void {
  const safe = safePath(path);
  if (safe) sessionStorage.setItem(RETURN_TO_KEY, safe);
}

export function returnToFromSearch(search: string): string {
  const queryValue = safePath(new URLSearchParams(search).get("returnTo"));
  if (queryValue) {
    rememberReturnTo(queryValue);
    return queryValue;
  }
  return safePath(sessionStorage.getItem(RETURN_TO_KEY)) ?? "/workspaces";
}

export function consumeReturnTo(search = ""): string {
  const destination = returnToFromSearch(search);
  sessionStorage.removeItem(RETURN_TO_KEY);
  return destination;
}

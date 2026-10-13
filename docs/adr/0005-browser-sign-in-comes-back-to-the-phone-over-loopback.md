# 5. Browser sign-in comes back to the phone over loopback, and the app keeps an access token of its own

## Context

Some memos servers sign people in only through another site, such as Google (memos calls these
identity providers). The provider redirects the browser to an address registered on the server's
OAuth client, normally the memos web app's `/auth/callback`, which a phone app cannot receive.
Google refuses sign-in inside an embedded WebView. Catching an `https` redirect with Android App
Links needs a file served from the memos domain and a fixed signing key. Memos' sign-in replies
with a short-lived session token, while sync wants a token that lasts.

## Decision

- The app opens the provider's sign-in page in the browser with `redirect_uri` set to
  `http://127.0.0.1:47815/capynib`, served for the length of the sign-in by `LoopbackReceiver`.
  It sends a random `state` and a PKCE challenge; a return without the right `state` is answered
  and ignored.
- The code goes to memos `auth/signin` as SSO credentials with the same redirect URI and the PKCE
  verifier. With the session token it gets, the app creates a personal access token for itself that
  expires after 30 days, and keeps only that, as if the user had pasted it.
- The server's first OAuth2 provider is used; the command line's `login` does the same.

## Consequences

- The server's admin adds `http://127.0.0.1:47815/capynib` to the OAuth client's authorized
  redirect URIs once. Until then the provider shows its own redirect error and the app keeps
  waiting until sign-in is tapped again.
- The port is fixed so it can be registered; if another app holds it, sign-in fails with a message
  and pasting an access token still works.
- Each sign-in leaves a named access token on the server, visible and revocable in memos settings.
- Thirty days after signing in, sync stops with the token refused, and the user signs in again. A
  leaked token is good for at most that long.

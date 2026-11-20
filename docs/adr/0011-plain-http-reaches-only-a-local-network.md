# 11. Plain http reaches only a local network

## Context

Self-hosters often run memos on a home network at an address like `http://192.168.1.10:5230`.
Android refuses unencrypted traffic by default from target SDK 28, so such an address was accepted
by Settings and then never reached. Allowing unencrypted traffic everywhere would let the access
token and the notes cross the internet in the clear when an address is typed without `https`.

## Decision

- The app's network security config permits cleartext, and the memos client's `PlainHttp`
  interceptor narrows it: a plain `http://` request goes out only when the connected address is
  loopback, link-local, private (RFC 1918), shared (100.64.0.0/10, used by VPNs such as Tailscale)
  or an IPv6 unique local address. Anything else fails before the request is written, with a
  message naming the host and the address it resolved to.
- The check runs on the address the connection reached, after DNS, so a local-sounding name that
  resolves to a public address is refused too.

## Consequences

- A LAN server works over `http://`; a public one needs `https://`.
- On a network that routes through a proxy, the address checked is the proxy's.
- The command line follows the same rule.

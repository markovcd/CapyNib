# 9. Recordings and photos are attachments of their note's memo

## Context

Keep lets a note hold voice clips, played from the note and its card, and images, shown at the top
of the note and its card. Memos (0.28 to 0.31) stores files as attachments linked to a memo:
`POST /api/v1/attachments` takes the bytes as base64 with a chosen `attachmentId` and the `memo` to
link to, each memo lists its attachments, and the bytes are served at
`/file/attachments/{id}/{filename}` with the access token. Creating an attachment under an id that is
taken answers 500, not 409.

## Decision

- A recording is an AAC clip in an MPEG-4 file (`audio/mp4`); a photo is the image as the camera or
  the phone's photo picker gave it, of its own `image/*` type. Each is kept on the device in a file
  named by its id, and synced as an attachment of its note's memo under that same id. Both live in
  one table, `attachment`, told apart by type.
- A note's recordings and photos follow its memo's audio and image attachments: one added in the memos
  web app is downloaded, one deleted there is deleted here, and attachments of any other type are
  left alone.
- An attachment is sent once its note's memo exists; a note that holds only recordings or photos is
  sent with empty text. One whose reply was lost is found on the memo by the next pull, so nothing
  depends on the 500 a second create would get.
- Deleting a recording or photo leaves a tombstone until the server has deleted its attachment.
  Deleting a note forever deletes its memo, which takes the attachments with it.
- Recordings and photos are not part of a note's content: they do not make a conflict, and a
  conflict copy has none.

## Consequences

- A file goes up in one request, base64 in JSON, so it is limited by the server's upload size
  (30 MB by default: about an hour of speech at 64 kbit/s, or any phone photo).
- Photos are sent as taken, not shrunk; the screen decodes them at the size it draws them, turned
  upright by their EXIF orientation.
- A clip recorded elsewhere has no length until it is played.
- Clips are not transcribed into the note's text, and photos are not read for text, as Keep does.

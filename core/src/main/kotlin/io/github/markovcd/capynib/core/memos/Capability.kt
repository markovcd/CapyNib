package io.github.markovcd.capynib.core.memos

/** One call sync makes to a memos server, in the order [ServerCheck] tries them. */
enum class Capability(val key: String, val what: String) {
    SignedInUser("signed-in-user", "read the signed-in user"),
    ListMemos("list-memos", "list memos"),
    CreateWithId("create-with-id", "create a memo under Capynib's own id and create time"),
    Update("update", "change a memo's text, pin, archive and create time"),
    ListArchived("list-archived", "list archived memos"),
    Delete("delete", "delete a memo"),
}

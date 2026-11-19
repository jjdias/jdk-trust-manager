# Agent behavior

Read this file before any other project file. It applies to every agent on this repo.

## Read once

- `roadmap.md` is the product spec, and its "Build order" section is the order of work. Do not rediscover, summarize, or rewrite it unless the user asks.
- If the user names a build step, read that step only. Do not read the rest of the plan again in the same session.
- Do not open `%LOCALAPPDATA%\JdkTrustManager\cache\`, zip files, `C:\Java`, or `cacerts`. Those are large binaries.

## Do the asked change and stop

- Implement the step the user named. Do not start the next step, add a screen, or "prepare" later work.
- Do not add dependencies, settings, or abstractions for a later step.
- Do not create extra markdown, diagrams, or comments that restate the spec.
- Do not commit, push, or change git config unless the user asks.

## Tool use

- Search only when you do not already know the file. One search, then read the matching lines. Do not fan out parallel greps for synonyms.
- Do not launch subagents for a single file, a known path, or a decision the roadmap already made.
- Do not re-run a command that just succeeded. If a command fails, read the error and change the command. Do not retry the same command.
- The shell is Windows PowerShell. Do not use `&&` or bash heredocs. Do not call `winget`, `msiexec`, `setx`, or Scoop. JDK install is the zip flow in the roadmap.
- Do not download a JDK, a CA bundle, or a TestNG certificate unless the current step says to.
- Do not start the GUI, a browser, or a long-running process to "check" a unit-test change.

## Editing

- Patch the smallest range that implements the step. Do not rewrite a file you did not need to change.
- Do not reformat or rename unrelated code.
- Tests cover the behavior you changed. Do not run the full Maven reactor after a comment or a single parser tweak. Run the one test class that covers it.
- If a test fails, fix the cause. Do not loosen the assertion to go green.

## Talking

- Lead with the result. Do not narrate the files you opened or the searches you skipped.
- Do not paste the roadmap back to the user.
- If the spec already answers the question, follow it. Ask only when two spec statements conflict.

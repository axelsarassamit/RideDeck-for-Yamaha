# RideDeck for Yamaha 0.12.22

Voice to text now starts text dictation even when a notification has no direct reply action. It no longer silently switches to opening the original app. Voice-message mode opens the conversation only when explicitly selected.

Reply actions survive notification dismissal and refresh on identical-message updates without moving the inbox item or invalidating an open reader. Reply extraction checks regular, wearable and Android Auto invisible actions, prefers an explicit semantic Reply action, and excludes other semantic actions. The send intent includes the original RemoteInput keys, free-form source and foreground flag.

The review contains editable text. Empty replies remain in the form. Missing, cancelled or rejected reply controls retain the draft and explain the problem, with explicit Copy text and Open conversation controls. Nothing is sent until Send is pressed. Apps must expose a direct reply action for sending inside RideDeck; the app does not automate another app's composer.

Regression tests cover late reply metadata, inbox order and identity, seen messages, recipient separation and action selection. Phone checks use an isolated temporary package and a receiver in that same package; no real messages are sent. Real speech recognition and messaging-app delivery must be distinguished from synthetic recognizer results and local receiver tests.

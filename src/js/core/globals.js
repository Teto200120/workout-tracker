// Transitional DOM aliases remain global because every screen uses them.
// Replacing them would create a large mechanical diff without improving feature ownership.
globalThis.$ = (id) => document.getElementById(id);
globalThis.all = (selector) => Array.from(document.querySelectorAll(selector));

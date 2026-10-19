export const $ = (id) => document.getElementById(id);
export const all = (selector) =>
  Array.from(document.querySelectorAll(selector));

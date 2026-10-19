// Bound both fetch and response-body consumption, even for a stalled adapter.
export async function withDeadline(
  task,
  milliseconds,
  { controller = new AbortController() } = {},
) {
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(() => task(controller.signal)),
      new Promise((_, reject) => {
        timer = setTimeout(() => {
          controller.abort();
          reject(new Error("Operation timed out."));
        }, milliseconds);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

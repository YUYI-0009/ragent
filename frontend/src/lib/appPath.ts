// 线上通过子路径部署（例如 /rag-agent-test-x9k3m7/），开发环境 BASE_URL 为 /
// 页面内所有需要写死的绝对跳转都必须经过这里，避免绕开 Router 的 basename
export const BASE_PATH = import.meta.env.BASE_URL.replace(/\/+$/, "");

export function appUrl(path = ""): string {
  const normalized = path.replace(/^\/+/, "");
  return normalized ? `${BASE_PATH}/${normalized}` : `${BASE_PATH}/`;
}

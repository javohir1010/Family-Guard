import axios from "axios";
import { useAuthStore } from "../store/authStore";

const API_BASE = "/api/v1";

export const apiClient = axios.create({
  baseURL: API_BASE,
  headers: { "Content-Type": "application/json" },
});

// Attach JWT token to every request
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 — refresh token or logout
apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      try {
        const refresh = useAuthStore.getState().refreshToken;
        if (!refresh) throw new Error("No refresh token");
        const { data } = await axios.post(`${API_BASE}/auth/refresh/`, {
          refresh,
        });
        useAuthStore.getState().setTokens(data.access, refresh);
        original.headers.Authorization = `Bearer ${data.access}`;
        return apiClient(original);
      } catch {
        useAuthStore.getState().logout();
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

// --- API helpers ---
export const authApi = {
  login: (email: string, password: string) =>
    apiClient.post("/auth/login/", { email, password }),
  register: (data: object) => apiClient.post("/auth/register/", data),
  logout: (refresh: string) => apiClient.post("/auth/logout/", { refresh }),
  profile: () => apiClient.get("/auth/profile/"),
};

export const familyApi = {
  create: (name: string) => apiClient.post("/family/create/", { name }),
  detail: () => apiClient.get("/family/detail/"),
  members: () => apiClient.get("/family/members/"),
  generateInvite: () => apiClient.post("/family/invite/generate/"),
  join: (code: string, device_name?: string) =>
    apiClient.post("/family/invite/join/", { code, device_name }),
  removeMember: (userId: string) =>
    apiClient.delete(`/family/members/${userId}/remove/`),
};

export const locationApi = {
  currentPositions: () => apiClient.get("/location/current/"),
  history: (childId: string, params?: object) =>
    apiClient.get(`/location/history/${childId}/`, { params }),
  zones: () => apiClient.get("/location/zones/"),
  createZone: (data: object) => apiClient.post("/location/zones/", data),
  deleteZone: (id: string) => apiClient.delete(`/location/zones/${id}/`),
};

export const sosApi = {
  history: () => apiClient.get("/sos/history/"),
  resolve: (id: string) => apiClient.post(`/sos/${id}/resolve/`),
};

export const dnsApi = {
  blocklist: () => apiClient.get("/dns/blocklist/"),
  blocked: () => apiClient.get("/dns/blocked/"),
  addBlock: (domain: string, reason?: string) =>
    apiClient.post("/dns/blocked/", { domain, reason }),
  removeBlock: (id: string) => apiClient.delete(`/dns/blocked/${id}/`),
  queries: (params?: object) => apiClient.get("/dns/queries/", { params }),
  categories: () => apiClient.get("/dns/categories/"),
  toggleCategory: (id: number) =>
    apiClient.patch(`/dns/categories/${id}/toggle/`),
};

export const appsApi = {
  usage: (childId: string, params?: object) =>
    apiClient.get(`/apps/usage/${childId}/`, { params }),
  rules: (childId?: string) =>
    childId ? apiClient.get(`/apps/rules/${childId}/`) : apiClient.get("/apps/rules/"),
  createRule: (data: object) => apiClient.post("/apps/rules/", data),
  updateRule: (id: string, data: object) =>
    apiClient.patch(`/apps/rules/${id}/detail/`, data),
  deleteRule: (id: string) => apiClient.delete(`/apps/rules/${id}/detail/`),
  schedules: (childId?: string) =>
    childId
      ? apiClient.get(`/apps/schedule/${childId}/`)
      : apiClient.get("/apps/schedule/"),
  createSchedule: (data: object) => apiClient.post("/apps/schedule/", data),
  permissionAlerts: () => apiClient.get("/apps/permission-alerts/"),
};

export const notificationsApi = {
  list: () => apiClient.get("/notifications/"),
  markAllRead: () => apiClient.post("/notifications/mark-all-read/"),
};

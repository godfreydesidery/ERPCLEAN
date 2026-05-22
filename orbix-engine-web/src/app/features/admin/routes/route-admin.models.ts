/** Mirrors the backend admin route DTOs (RouteController). */

export interface Route {
  id: string;
  uid: string;
  companyId: string;
  code: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
}

export interface CreateRouteRequest {
  code: string;
  name: string;
  description: string | null;
}

export interface UpdateRouteRequest {
  name: string;
  description: string | null;
}

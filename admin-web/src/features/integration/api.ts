import { request } from '../../api/request'
export interface Settings { provider:string;baseUrl:string;orgId:string;appId:string;enabled:boolean;version:number }
export interface SettingsInput { provider:string;baseUrl:string;orgId:string;appId:string;enabled:boolean;expectedVersion:number;clientSecret?:string;accessToken?:string }
export function getSettings(){return request<Settings>('/admin/integration/settings')}
export function saveSettings(input:SettingsInput){return request<Settings>('/admin/integration/settings',{method:'PUT',body:JSON.stringify(input)})}

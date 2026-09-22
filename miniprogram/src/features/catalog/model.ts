export interface CatalogWarehouse { id:string; name:string }
export interface CatalogStop { id:string; name:string; enabled?:boolean; available?:boolean }
export interface BookingSlot { id:string; start:string; end:string; remaining:number; available:boolean }
export interface BookingRules { bookingDays:number; descriptionMaxLength:number; sizeMaxLength:number; remarkMaxLength:number }
export function canSelectSlot(slot:BookingSlot, now=Date.now()) {
  return slot.available && slot.remaining>0 && Date.parse(slot.start)>now && Date.parse(slot.end)-Date.parse(slot.start)===30*60*1000
}
export function bookingDates(days:number,now=Date.now()) {
  const day=new Date(now+8*3600000).toISOString().slice(0,10)
  return Array.from({length:Math.max(0,Math.min(days,366))},(_,i)=>new Date(Date.parse(`${day}T00:00:00Z`)+i*86400000).toISOString().slice(0,10))
}
export function canSelectStop(stop:CatalogStop) { return stop.enabled!==false && stop.available!==false }
export function slotText(slot:Pick<BookingSlot,'start'|'end'>) { return `${chinaTime(slot.start)}–${chinaTime(slot.end).slice(11)}` }
export function chinaTime(value:string) { const date=new Date(value); return Number.isNaN(date.getTime())?'—':new Date(date.getTime()+8*3600000).toISOString().slice(0,16).replace('T',' ') }

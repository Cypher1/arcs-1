import {logFactory} from '../../../build/platform/log-web.js';
import {Xen} from '../components/xen.js';
import {ArcComponentMixin} from '../components/arc-component.js';

const log = logFactory('ArcElement', '#bb1396');

const template = Xen.Template.html`
  <style>
    :host {
      display: block;
    }
    [slotid="modal"] {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      box-sizing: border-box;
      pointer-events: none;
    }
  </style>
  <div slotid="toproot"></div>
  <div slotid="root"></div>
  <div slotid="modal"></div>
`;

const ArcElementMixin = Base => class extends Base {
  get template() {
    return template;
  }
  _didMount() {
    this.containers = {
      toproot: this.host.querySelector('[slotid="toproot"]'),
      root: this.host.querySelector('[slotid="root"]'),
      modal: this.host.querySelector('[slotid="modal"]')
    };
  }
};

export const ArcElement = Xen.Debug(ArcElementMixin(ArcComponentMixin(Xen.AsyncMixin(Xen.Base))), log);

customElements.define('arc-element', Xen.Debug(ArcElement, log));
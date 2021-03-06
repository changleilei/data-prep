import { call } from 'redux-saga/effects';
import * as effects from '../effects/bootstrap.effects';

export function* bootstrap() {
	yield call(effects.bootstrap);
	yield call(effects.setLanguage);
}

export default {
	bootstrap,
};
